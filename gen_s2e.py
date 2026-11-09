#!/usr/bin/env python3
import io, json

def J(o):
    return json.dumps(o, separators=(",", ":"))

cases = []
A = cases.append

def case(cid, call, expect):
    A('  {:id %s :op :sse\n   :call %s\n   :expect {:value %s}}' % (cid, call, expect))

def S(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('\n', '\\n').replace('\r', '\\r') + '"'

# --- parse-text end to end
body = 'data: %s\n\ndata: [DONE]\n' % J({"choices": [{"delta": {"content": "hi"}, "finish_reason": "stop"}]})
case(':sse/parse-simple-content', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "hi"}}] "model" nil "usage" nil}')

body = ': keep-alive\n\nevent: ping\ndata: %s\n\n' % J({"choices": [{"delta": {"content": "yo"}, "finish_reason": "stop"}]})
case(':sse/parse-comment-blank-noise', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "yo"}}] "model" nil "usage" nil}')

body = 'data: %s\r\n\r\ndata: [DONE]\r\n' % J({"choices": [{"delta": {"content": "cr"}, "finish_reason": "stop"}]})
case(':sse/parse-crlf', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "cr"}}] "model" nil "usage" nil}')

body = 'data: {oops\n\ndata: %s\n\n' % J({"choices": [{"delta": {"content": "ok"}, "finish_reason": "stop"}]})
case(':sse/parse-malformed-json', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "ok"}}] "model" nil "usage" nil}')

c1 = J({"choices": [{"delta": {"tool_calls": [{"index": 0, "id": "t1", "function": {"name": "run", "arguments": '{"a":'}}]}, "finish_reason": None}]})
c2 = J({"choices": [{"delta": {"tool_calls": [{"index": 0, "function": {"arguments": '1}'}}]}, "finish_reason": "tool_calls"}]})
body = 'data: %s\ndata: %s\n' % (c1, c2)
case(':sse/parse-tool-call-split', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "tool_calls" "message" {"role" "assistant" "content" nil "tool_calls" [{"id" "t1" "type" "function" "function" {"name" "run" "arguments" "{\\"a\\":1}"}}]}}] "model" nil "usage" nil}')

c1 = J({"choices": [{"delta": {"tool_calls": [{"index": 1, "id": "b", "function": {"name": "b2", "arguments": "{}"}}]}}]})
c2 = J({"choices": [{"delta": {"tool_calls": [{"index": 0, "id": "a", "function": {"name": "a1", "arguments": "{}"}}]}, "finish_reason": "tool_calls"}]})
body = 'data: %s\ndata: %s\n' % (c1, c2)
case(':sse/parse-multi-index-tools', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "tool_calls" "message" {"role" "assistant" "content" nil "tool_calls" [{"id" "a" "type" "function" "function" {"name" "a1" "arguments" "{}"}} {"id" "b" "type" "function" "function" {"name" "b2" "arguments" "{}"}}]}}] "model" nil "usage" nil}')

body = 'data: %s\ndata: %s\n' % (J({"choices": [{"delta": {"reasoning_content": "think"}}]}),
      J({"choices": [{"delta": {"content": "say"}, "finish_reason": "stop"}]}))
case(':sse/parse-reasoning', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "say" "reasoning_content" "think"}}] "model" nil "usage" nil}')

body = 'data: %s\n' % J({"model": "m", "usage": {"prompt_tokens": 1, "completion_tokens": 2, "total_tokens": 3},
                          "choices": [{"delta": {"content": "u"}, "finish_reason": "stop"}]})
case(':sse/parse-usage-model', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "u"}}] "model" "m" "usage" {"prompt_tokens" 1 "completion_tokens" 2 "total_tokens" 3}}')

body = 'data: %s\n' % J({"timings": {"prompt_n": 4, "predicted_n": 6}, "choices": [{"delta": {"content": "t"}, "finish_reason": "stop"}]})
case(':sse/parse-usage-timings', '["parse-text" %s]' % S(body),
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "t"}}] "model" nil "usage" {"prompt_tokens" 4 "completion_tokens" 6 "total_tokens" 10}}')

body = 'data: %s\n' % J({"error": {"message": "bad key", "type": "auth"}})
case(':sse/parse-error-chunk', '["parse-text" %s]' % S(body),
     '{"error" {"message" "bad key" "type" "auth"}}')

case(':sse/parse-unseen', '["parse-text" ": keep-alive\\n\\n"]',
     '{"choices" []}')

case(':sse/parse-empty', '["parse-text" ""]',
     '{"choices" []}')

case(':sse/parse-data-empty-payload', '["parse-text" "data:\\ndata:   \\n"]',
     '{"choices" []}')

# --- finish direct
case(':sse/finish-error', '["finish" {:error {:message "x"}}]',
     '{"error" {:message "x"}}')
case(':sse/finish-unseen', '["finish" {:seen? false}]',
     '{"choices" []}')
case(':sse/finish-full', '["finish" {:seen? true :content "c" :reasoning "" :tools {"0" {:id "t" :name "n" :args "{}"}} :finish "stop" :model "m" :usage {:prompt_tokens 1}}]',
     '{"choices" [{"index" 0 "finish_reason" "stop" "message" {"role" "assistant" "content" "c" "tool_calls" [{"id" "t" "type" "function" "function" {"name" "n" "arguments" "{}"}}]}}] "model" "m" "usage" {:prompt_tokens 1}}')

# --- complete?
case(':sse/complete-done', '["complete?" {:done? true}]', 'true')
case(':sse/complete-finish', '["complete?" {:finish "stop"}]', 'true')
case(':sse/complete-error', '["complete?" {:error {:message "x"}}]', 'true')
case(':sse/complete-fragment', '["complete?" {:seen? true :content "half"}]', 'false')

# --- cooldown
case(':sse/cooldown-402', '["cooldown" {:status 402}]', '1800000')
case(':sse/cooldown-401', '["cooldown" {:status 401}]', '1800000')
case(':sse/cooldown-429', '["cooldown" {:status 429}]', '120000')
case(':sse/cooldown-500', '["cooldown" {:status 500}]', '300000')
case(':sse/cooldown-transport', '["cooldown" {:error "reset"}]', '300000')

# --- hop-error
longmsg = "e" * 500
case(':sse/hop-error-truncates', '["hop-error" {:provider "p" :model "m"} 500 %s]' % S(longmsg),
     '{:provider "p" :model "m" :status 500 :error %s}' % S("e" * 400))
case(':sse/hop-error-short', '["hop-error" {:provider "p" :model "m"} 200 "ok"]',
     '{:provider "p" :model "m" :status 200 :error "ok"}')

# --- acc shape
case(':sse/acc-shape', '["acc"]',
     '{:seen? false :done? false :content "" :reasoning "" :tools {} :finish nil :model nil :usage nil :error nil}')

block = "\n  ;; ---------------------------------------------------------- S2e sse\n" + "\n".join(cases) + "\n"

p = "otehon/test/cases.edn"
s = io.open(p, encoding="utf-8").read()
assert s.rstrip().endswith("]}]")
idx = s.rstrip().rfind("]}]")
s = s.rstrip()[:idx].rstrip() + "\n" + block + "]}]\n"
io.open(p, "w", encoding="utf-8").write(s)
print("cases:", len(cases))

m = []
m.append('  {:id :must-fail/sse-cooldown-expects-zero :op :sse\n   :call ["cooldown" {:status 429}]\n   :expect {:value 0}}')
m.append('  {:id :must-fail/sse-complete-fragment-expects-true :op :sse\n   :call ["complete?" {:seen? true :content "half"}]\n   :expect {:value true}}')
mblock = "\n".join(m) + "\n"
p2 = "otehon/test/must-fail.edn"
s2 = io.open(p2, encoding="utf-8").read()
assert s2.rstrip().endswith("]}]")
idx2 = s2.rstrip().rfind("]}]")
s2 = s2.rstrip()[:idx2].rstrip() + "\n" + mblock + "]}]\n"
io.open(p2, "w", encoding="utf-8").write(s2)
print("mustfail: 2")
