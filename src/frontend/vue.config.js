const { defineConfig } = require('@vue/cli-service')

const proxyTarget = process.env.FRAUD_API_PROXY_TARGET || 'http://localhost:8080'
const proxyOptions = {
  target: proxyTarget,
  ws: true,
  changeOrigin: true
}

module.exports = defineConfig({
  productionSourceMap: false,
  devServer: {
    port: 5173,
    proxy: {
      '/api': { ...proxyOptions },
      '/livez': { ...proxyOptions },
      '/readyz': { ...proxyOptions }
    }
  }
})
