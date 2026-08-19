/// 构建期注入的接入配置。CI 打包时通过环境变量提供，
/// 于是安装包开箱即用，用户机上无需设置任何环境变量。
/// Logto 的 issuer/client_id/api_resource 不在此列——它们由服务端启动时下发。
const INJECTED_KEYS: [&str; 1] = ["MINTPOP_SERVER_URL"];

fn main() {
    for key in INJECTED_KEYS {
        println!("cargo:rerun-if-env-changed={key}");
    }
    tauri_build::build()
}
