// Thin host. The itonami agent core (Otehon .cljc compiled to ClojureScript)
// runs in the WebView. No SQL, model, shell, network or filesystem commands.
#[tauri::command]
fn awai_host_info() -> serde_json::Value {
    serde_json::json!({
        "schema": "awai.shell.host.v1",
        "app": "cloud.itonami.agent",
        "external_operations": []
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .runtime(tauri_runtime_wry::Wry::default())
        .invoke_handler(tauri::generate_handler![awai_host_info])
        .run(tauri::generate_context!())
        .expect("Itonami Agent initialization failed");
}
