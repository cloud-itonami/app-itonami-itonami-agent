; Otehon-only namespace port; kudaki@1948314013c4cdd93415de4f49fa3a0661086630/src/kudaki/material.cljk
(ns itonami.otehon.physics.kudaki.material
  "Constitutive updates — the stress response σ^{n+1} = f(σ^n, Δε, state).

  In an explicit code the material model is called once per element per step
  with the small strain INCREMENT Δε accumulated from the velocity field, and it
  returns the new stress plus whatever internal variables it carries (here, the
  equivalent plastic strain). Everything is a PURE function of the incoming
  state — no element owns mutable globals; the integrator threads the returned
  state forward. That is what makes a step checkpointable.

  Two models ship in S0:

    • linear elastic — Hooke's law. In 3-D it is built from the Lamé constants
      λ, μ derived from (E, ν); in 1-D (a truss) it is just σ = E ε.

    • J2 (von-Mises) plasticity with linear isotropic hardening, integrated by
      RADIAL RETURN MAPPING. This is the crash workhorse: thin-walled structure
      absorbs impact energy by plastic folding, and J2 is the baseline metal
      plasticity model. The return map is an elastic predictor / plastic
      corrector — trial the stress elastically, and if it pierces the yield
      surface, scale the DEVIATOR straight back onto it along its own radius
      (the pressure is untouched — metals yield by shear, not compression).

  Voigt convention follows itonami.otehon.physics.kudaki.linalg: [xx yy zz xy yz zx], engineering shear
  in strain. The radial return is done entirely in STRESS space, so it never has
  to reason about engineering-vs-tensor shear — dev/von-Mises carry the factors."
  (:require [itonami.otehon.physics.kudaki.linalg :as la]))

;; ---------------------------------------------------------------------------
;; Elastic constants
;; ---------------------------------------------------------------------------

(defn lame
  "Lamé constants {:lambda λ :mu μ} from Young's modulus E and Poisson ν.
      μ = E / (2(1+ν))                shear modulus (G)
      λ = Eν / ((1+ν)(1−2ν))          dilatational coupling
  μ is the deviatoric stiffness J2 scales the return by; λ+2μ is the P-wave
  modulus that sets the dilatational wave speed / critical step in a solid."
  [E nu]
  {:lambda (/ (* E nu) (* (+ 1.0 nu) (- 1.0 (* 2.0 nu))))
   :mu     (/ E (* 2.0 (+ 1.0 nu)))})

(defn elastic-stress-increment
  "Δσ = C : Δε for 3-D isotropic linear elasticity (Voigt, engineering shear).
      Δσ_normal = λ tr(Δε) + 2μ Δε_normal
      Δσ_shear  = μ Δγ        (engineering shear ⇒ the 2 is already in γ)"
  [{:keys [lambda mu]} deps]
  (let [tr (la/trace deps)
        a  (* lambda tr)]
    [(+ a (* 2.0 mu (deps 0)))
     (+ a (* 2.0 mu (deps 1)))
     (+ a (* 2.0 mu (deps 2)))
     (* mu (deps 3))
     (* mu (deps 4))
     (* mu (deps 5))]))

;; ---------------------------------------------------------------------------
;; 3-D constitutive update (elastic + J2 radial return)
;; ---------------------------------------------------------------------------

(declare stress-update-jc-3d stress-update-combined-3d)

(defn stress-update-3d
  "Advance the 3-D stress one step. `mat` is
      {:type :elastic|:j2 :E :nu [:yield σy0 :hardening H]}
  `state` carries {:eq-plastic-strain ε̄p}. Given the old stress σⁿ and the
  strain increment Δε (Voigt), return {:stress σ^{n+1} :state state'
  :plastic-work Δwₚ}.

  Radial return (J2, linear isotropic hardening):
    1. elastic trial   σᵗ = σⁿ + C:Δε
    2. trial vM stress σ̄ᵗ = √(3/2) |dev σᵗ|
    3. yield check     f  = σ̄ᵗ − (σy0 + H ε̄pⁿ)
    4. if f ≤ 0 → elastic, keep σᵗ.
       else plastic multiplier  Δλ = f / (3μ + H)   (consistency: the deviator
       relaxes by 3μ Δλ exactly onto the grown yield surface), scale the
       deviator  s ← s (σ̄ᵗ−3μΔλ)/σ̄ᵗ, leave the pressure, and grow ε̄p by Δλ.
    Plastic work increment ≈ σ̄_mid · Δλ feeds the energy ledger's dissipation."
  [mat sig deps state]
  (case (:type mat)
    :johnson-cook (stress-update-jc-3d mat sig deps state)
    :combined     (stress-update-combined-3d mat sig deps state)   ; iso+kinematic 3-D
   (let [{:keys [E nu]} mat
        cE   (lame E nu)
        mu   (:mu cE)
        sigT (la/t+ sig (elastic-stress-increment cE deps))]
    (if (not= (:type mat) :j2)
      {:stress sigT :state state :plastic-work 0.0}
      (let [{:keys [yield hardening]} mat
            H     (double (or hardening 0.0))
            ep    (double (or (:eq-plastic-strain state) 0.0))
            sdev  (la/deviator sigT)
            qtr   (Math/sqrt (* 1.5 (la/ddot sdev sdev)))     ; trial von-Mises
            sy    (+ yield (* H ep))
            f     (- qtr sy)]
        (if (<= f 0.0)
          {:stress sigT :state state :plastic-work 0.0}
          (let [dl   (/ f (+ (* 3.0 mu) H))                   ; plastic multiplier
                qnew (- qtr (* 3.0 mu dl))                    ; = sy0 + H(ep+dl)
                scl  (/ qnew qtr)
                snew (la/tscale scl sdev)
                p    (la/mean sigT)
                signew [(+ (snew 0) p) (+ (snew 1) p) (+ (snew 2) p)
                        (snew 3) (snew 4) (snew 5)]]
            {:stress signew
             :state  (assoc state :eq-plastic-strain (+ ep dl))
             :plastic-work (* 0.5 (+ qtr qnew) dl)})))))))

;; ---------------------------------------------------------------------------
;; Johnson-Cook (rate- and temperature-dependent J2) — the crash workhorse
;; ---------------------------------------------------------------------------

(defn johnson-cook-flow-stress
  "Johnson-Cook flow stress σy(ε̄p, ε̄ṗ, T) — the multiplicative split that makes
  it the standard high-rate metal model for crash/impact:

      σy = (A + B ε̄p^n) · (1 + C ln ε*) · (1 − T*^m)
           └─ strain hardening ─┘ └ strain rate ┘ └ thermal softening ┘

      ε* = max(ε̄ṗ / ε̇₀, 1)               rate ratio, clamped so sub-reference
                                          rates never *soften* (ln ≤ 0 floored)
      T* = clamp((T − T_room)/(T_melt − T_room), 0, 1)   homologous temperature

  `mat` carries {:A :B :n :C :m :eps0 :Troom :Tmelt}; sensible defaults make the
  rate term vanish (C 0) and the thermal term vanish (T = T_room) so a pure
  hardening law is the C=0, isothermal special case. Returns a stress in Pa."
  [{:keys [A B n C m eps0 Troom Tmelt]} ep ep-rate T]
  (let [n      (double (or n 1.0))
        C      (double (or C 0.0))
        m      (double (or m 1.0))
        eps0   (double (or eps0 1.0))
        Troom  (double (or Troom 293.0))
        Tmelt  (double (or Tmelt 1.0e30))
        T      (double (or T Troom))
        hard   (+ A (* B (Math/pow (max ep 0.0) n)))
        ratio  (max (/ (max ep-rate 0.0) eps0) 1.0)        ; floor at 1 ⇒ ln ≥ 0
        rate   (+ 1.0 (* C (Math/log ratio)))
        Tstar  (max 0.0 (min 1.0 (/ (- T Troom) (- Tmelt Troom))))
        therm  (- 1.0 (Math/pow Tstar m))]
    (* hard rate therm)))

(defn stress-update-jc-3d
  "3-D radial return with a Johnson-Cook flow stress. Identical deviatoric
  geometry to `stress-update-3d`'s J2 path — the only difference is that the
  yield radius is the rate/temperature-dependent JC stress, which makes the
  consistency condition NONLINEAR in the plastic multiplier Δλ:

      r(Δλ) = q_trial − 3μ Δλ − σy^JC(ε̄p+Δλ, Δλ/Δt, T) = 0

  r is monotone-decreasing on [0, q_trial/3μ] (hardening and the 3μΔλ pull-back
  both shrink it), r(0) > 0 when yielding and r(q_trial/3μ) = −σy < 0, so a
  bracketed BISECTION finds the unique root robustly without needing the messy
  analytic tangent of the ln-rate term. The plastic strain rate ε̄ṗ = Δλ/Δt is
  read self-consistently inside the iteration; Δt comes from (:dt state) (absent
  ⇒ quasi-static, rate factor 1). Temperature is (:temperature state)/`:Troom`.

  Returns the same {:stress :state :plastic-work} contract as the J2 update."
  [mat sig deps state]
  (let [{:keys [E nu]} mat
        cE   (lame E nu)
        mu   (:mu cE)
        sigT (la/t+ sig (elastic-stress-increment cE deps))
        ep   (double (or (:eq-plastic-strain state) 0.0))
        dt   (double (or (:dt state) 0.0))
        T    (:temperature state)
        sdev (la/deviator sigT)
        qtr  (Math/sqrt (* 1.5 (la/ddot sdev sdev)))
        rate (fn [dl] (if (pos? dt) (/ dl dt) 0.0))
        flow (fn [dl] (johnson-cook-flow-stress mat (+ ep dl) (rate dl) T))]
    (if (<= (- qtr (flow 0.0)) 0.0)
      {:stress sigT :state state :plastic-work 0.0}
      (let [dl-max (/ qtr (* 3.0 mu))
            dl     (loop [lo 0.0 hi dl-max i 0]
                     (let [mid (* 0.5 (+ lo hi))
                           r   (- qtr (* 3.0 mu mid) (flow mid))]
                       (if (or (= i 80) (< (- hi lo) (* 1.0e-14 (max 1.0 dl-max))))
                         mid
                         (if (pos? r) (recur mid hi (inc i)) (recur lo mid (inc i))))))
            qnew (- qtr (* 3.0 mu dl))                      ; = σy^JC on the surface
            scl  (/ qnew qtr)
            snew (la/tscale scl sdev)
            p    (la/mean sigT)
            signew [(+ (snew 0) p) (+ (snew 1) p) (+ (snew 2) p)
                    (snew 3) (snew 4) (snew 5)]]
        {:stress signew
         :state  (assoc state :eq-plastic-strain (+ ep dl))
         :plastic-work (* 0.5 (+ qtr qnew) dl)}))))

(defn adiabatic-temperature-rise
  "Self-heating from plastic work — the coupling that closes the Johnson-Cook
  loop. At crash strain rates there is no time for heat to diffuse, so the plastic
  work stays local and raises the temperature ADIABATICALLY:

      ΔT = β Δwₚ / (ρ cₚ)

  β is the Taylor–Quinney coefficient (the fraction of plastic work that becomes
  heat, ~0.9 for metals; the rest is stored in the dislocation structure). That
  ΔT feeds straight back into the JC thermal-softening factor on the next step,
  so a fast-deforming shear band heats, softens, and localizes further — the
  physical mechanism of adiabatic shear failure.

  Used in a STAGGERED (explicit) split: the return map of step n uses the
  temperature accumulated up to n, then the integrator adds this ΔT for n+1.
  `mat` supplies {:rho :cp [:beta 0.9]}; returns ΔT in kelvin."
  [{:keys [rho cp beta]} plastic-work]
  (let [beta (double (or beta 0.9))]
    (/ (* beta plastic-work) (* (double rho) (double cp)))))

;; ---------------------------------------------------------------------------
;; Ductile damage & element erosion (Johnson-Cook fracture)
;; ---------------------------------------------------------------------------

(defn stress-triaxiality
  "Stress triaxiality η = σ_m / σ̄ (mean / von-Mises) — the single most important
  driver of ductile fracture. η = 1/3 in uniaxial tension, 0 in pure shear, and
  large under the hydrostatic tension of a notch/spotweld, where metals fail at a
  fraction of their uniaxial ductility. Guarded to 0 when the deviator vanishes."
  [sig]
  (let [vm (la/von-mises sig)]
    (if (< vm 1.0e-12) 0.0 (/ (la/mean sig) vm))))

(defn johnson-cook-fracture-strain
  "Johnson-Cook failure strain ε_f(η, ε̄ṗ, T) — the strain a point can plastically
  accumulate before it fractures, with the SAME multiplicative structure as the
  flow stress but driven by triaxiality:

      ε_f = [D1 + D2 e^{D3 η}] · [1 + D4 ln ε*] · [1 + D5 T*]

  D3 is negative for metals, so rising triaxiality η exponentially *cuts* the
  available ductility. `mat` supplies {:D1 :D2 :D3 [:D4 :D5 :eps0 :Troom :Tmelt]};
  rate is floored at the reference (ln ≥ 0) and T* is the homologous temperature.
  Floored at a tiny positive value so the damage rate stays finite."
  [{:keys [D1 D2 D3 D4 D5 eps0 Troom Tmelt]} eta ep-rate T]
  (let [D4    (double (or D4 0.0))
        D5    (double (or D5 0.0))
        eps0  (double (or eps0 1.0))
        Troom (double (or Troom 293.0))
        Tmelt (double (or Tmelt 1.0e30))
        T     (double (or T Troom))
        ratio (max (/ (max ep-rate 0.0) eps0) 1.0)
        Tstar (max 0.0 (min 1.0 (/ (- T Troom) (- Tmelt Troom))))
        ef    (* (+ D1 (* D2 (Math/exp (* D3 eta))))
                 (+ 1.0 (* D4 (Math/log ratio)))
                 (+ 1.0 (* D5 Tstar)))]
    (max ef 1.0e-6)))

(defn accumulate-damage
  "Advance the dimensionless damage `D` by a plastic-strain increment under the
  current fracture strain: D ← D + Δε̄p / ε_f. The element is ERODED (removed from
  the internal-force assembly, LS-DYNA *MAT_ADD_EROSION style) once D ≥ 1.
  Returns {:damage D' :eroded? bool}."
  [damage d-eq-plastic frac-strain]
  (let [d' (+ (double damage) (/ (double d-eq-plastic) (double frac-strain)))]
    {:damage d' :eroded? (>= d' 1.0)}))

;; ---------------------------------------------------------------------------
;; 1-D constitutive update (truss / rod axial stress)
;; ---------------------------------------------------------------------------

(declare axial-update-kinematic)

(defn axial-update
  "Advance the uniaxial (rod) stress one step. A truss carries a single normal
  stress, so J2 collapses to the classic 1-D return: yield when |σ| exceeds the
  current flow stress, then split the trial overshoot between elastic and
  hardening compliance, Δεp = (|σᵗ|−σy)/(E+H).

  `mat` {:type :elastic|:j2|:kinematic :E [:yield :hardening]}, `state`
  {:eq-plastic-strain ε̄p}. Returns {:stress σ :state state' :plastic-work Δwₚ}.
  `:kinematic` routes to `axial-update-kinematic` so a truss can use the Bauschinger
  model end-to-end (the 1-D mirror of `stress-update-3d`'s `:combined` dispatch)."
  [mat sig deps state]
  (if (= (:type mat) :kinematic)
    (axial-update-kinematic mat sig deps state)
   (let [E (:E mat)
        sigT (+ sig (* E deps))]
    (if (not= (:type mat) :j2)
      {:stress sigT :state state :plastic-work 0.0}
      (let [{:keys [yield hardening]} mat
            H  (double (or hardening 0.0))
            ep (double (or (:eq-plastic-strain state) 0.0))
            sy (+ yield (* H ep))
            f  (- (Math/abs sigT) sy)]
        (if (<= f 0.0)
          {:stress sigT :state state :plastic-work 0.0}
          (let [dep (/ f (+ E H))
                sgn (if (neg? sigT) -1.0 1.0)
                snew (- sigT (* E dep sgn))]
            {:stress snew
             :state  (assoc state :eq-plastic-strain (+ ep dep))
             :plastic-work (* (Math/abs snew) dep)})))))))

(defn axial-update-kinematic
  "1-D axial update with LINEAR KINEMATIC hardening (Prager). The yield surface
  keeps a fixed width 2σy0 but TRANSLATES with a back stress β that evolves with
  plastic flow: f = |σ − β| − σy0, dβ = H_k dε̄p·sign(σ−β).

  This reproduces the BAUSCHINGER effect — after forward yielding the back stress
  shifts the elastic range, so reverse yield occurs at a *lower* magnitude and the
  elastic span on a full unload→reload reversal is exactly 2σy0, independent of how
  far the metal hardened. Isotropic hardening (`axial-update`) instead grows the
  surface symmetrically and cannot capture this — which matters for the cyclic
  load reversal of a crush fold.

  `mat` {:type :kinematic :E :yield σy0 :hardening H_k}, `state`
  {:back-stress β :eq-plastic-strain ε̄p}. Returns {:stress :state :plastic-work}."
  [mat sig deps state]
  (let [E    (:E mat)
        sigT (+ sig (* E deps))
        sy0  (:yield mat)
        Hk   (double (or (:hardening mat) 0.0))
        beta (double (or (:back-stress state) 0.0))
        ep   (double (or (:eq-plastic-strain state) 0.0))
        f    (- (Math/abs (- sigT beta)) sy0)]
    (if (<= f 0.0)
      {:stress sigT
       :state (assoc state :back-stress beta :eq-plastic-strain ep)
       :plastic-work 0.0}
      (let [dep  (/ f (+ E Hk))
            sgn  (if (neg? (- sigT beta)) -1.0 1.0)
            snew (- sigT (* E dep sgn))
            bnew (+ beta (* Hk dep sgn))]
        {:stress snew
         :state  (assoc state :back-stress bnew :eq-plastic-strain (+ ep dep))
         :plastic-work (* (Math/abs (- snew bnew)) dep)}))))

;; ---------------------------------------------------------------------------
;; 3-D combined isotropic + kinematic hardening (the general J2 return)
;; ---------------------------------------------------------------------------

(defn stress-update-combined-3d
  "3-D radial return with BOTH linear isotropic and linear kinematic hardening —
  the general J2 model the 1-D `axial-update`/`axial-update-kinematic` specialize.
  A deviatoric **back-stress tensor** α (Voigt 6) shifts the yield surface while
  isotropic hardening grows it:

      ξ = dev(σ) − α        relative (shifted) deviatoric stress
      q = √(3/2 ξ:ξ)        its von-Mises measure
      f = q − (σy0 + H_iso ε̄p)

  On yielding, Δλ = f/(3μ + H_iso + H_kin); the deviator relaxes elastically by
  3μΔλ along ξ̂ while α advances by H_kin Δλ along ξ̂, so q drops by exactly
  (3μ+H_kin)Δλ onto the grown, shifted surface. Reduces to pure isotropic J2 at
  H_kin=0 and to 3-D kinematic at H_iso=0.

  `mat` {:type :combined :E :nu :yield σy0 :h-iso :h-kin}, `state`
  {:eq-plastic-strain ε̄p :back-stress α(Voigt6)}. Returns {:stress :state
  :plastic-work}."
  [mat sig deps state]
  (let [{:keys [E nu yield h-iso h-kin]} mat
        cE   (lame E nu)
        mu   (:mu cE)
        Hi   (double (or h-iso 0.0))
        Hk   (double (or h-kin 0.0))
        sigT (la/t+ sig (elastic-stress-increment cE deps))
        ep   (double (or (:eq-plastic-strain state) 0.0))
        alpha (or (:back-stress state) la/zero6)
        s    (la/deviator sigT)
        xi   (la/t- s alpha)
        q    (Math/sqrt (* 1.5 (la/ddot xi xi)))
        sy   (+ yield (* Hi ep))
        f    (- q sy)]
    (if (<= f 0.0)
      {:stress sigT
       :state (assoc state :eq-plastic-strain ep :back-stress alpha)
       :plastic-work 0.0}
      (let [dl    (/ f (+ (* 3.0 mu) Hi Hk))
            scl-s (/ (* 3.0 mu dl) q)            ; deviator elastic relaxation
            scl-a (/ (* Hk dl) q)               ; back-stress advance
            s-new (la/t- s (la/tscale scl-s xi))
            a-new (la/t+ alpha (la/tscale scl-a xi))
            p     (la/mean sigT)
            signew [(+ (s-new 0) p) (+ (s-new 1) p) (+ (s-new 2) p)
                    (s-new 3) (s-new 4) (s-new 5)]
            qnew  (- q (* (+ (* 3.0 mu) Hk) dl))]
        {:stress signew
         :state  (assoc state :eq-plastic-strain (+ ep dl) :back-stress a-new)
         :plastic-work (* 0.5 (+ q qnew) dl)}))))
