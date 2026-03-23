// Proxy API and WebSocket requests to the Ktor backend during local split-mode development.
// The Wasm/JS dev server runs on :8081, Ktor on :8080.
if (config.devServer) {
    config.devServer.proxy = [
        {
            context: ["/api"],
            target: "http://localhost:8080",
            changeOrigin: true,
        },
        {
            context: ["/ws"],
            target: "ws://localhost:8080",
            ws: true,
        },
    ];
}
