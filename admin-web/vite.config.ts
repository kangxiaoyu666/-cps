import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { fileURLToPath, URL } from "node:url";

export default defineConfig({
  plugins: [vue()],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("/node_modules/element-plus/") || id.includes("/node_modules/@element-plus/")) {
            return "element";
          }
          if (id.includes("/node_modules/vue/") || id.includes("/node_modules/vue-router/")
              || id.includes("/node_modules/pinia/")) {
            return "vue";
          }
          if (id.includes("/node_modules/axios/")) return "axios";
          return undefined;
        },
      },
    },
  },
  server: { port: 5173, proxy: { "/api": "http://localhost:8080" } },
});
