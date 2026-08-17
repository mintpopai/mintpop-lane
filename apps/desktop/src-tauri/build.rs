/// 构建期注入的接入配置。CI 打包时通过环境变量提供，
/// 于是安装包开箱即用，员工机上无需设置任何环境变量。
const INJECTED_KEYS: [&str; 4] = [
    "MINTPOP_LOGTO_ISSUER",
    "MINTPOP_LOGTO_CLIENT_ID",
    "MINTPOP_API_RESOURCE",
    "MINTPOP_SERVER_URL",
];

fn main() {
    for key in INJECTED_KEYS {
        println!("cargo:rerun-if-env-changed={key}");
    }
    tauri_build::build()
}
