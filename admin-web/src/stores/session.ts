import { defineStore } from "pinia";
import client, { login } from "../api/request";

export const useSessionStore = defineStore("session", {
  state: () => ({
    displayName: sessionStorage.getItem("admin-display-name") || "",
    tenantName: sessionStorage.getItem("admin-tenant-name") || "",
    authenticated: sessionStorage.getItem("admin-authenticated") === "true"
  }),
  actions: {
    async login(tenantCode: string, username: string, password: string) {
      const result = await login(tenantCode, username, password);
      this.displayName = result.displayName;
      this.tenantName = tenantCode;
      this.authenticated = true;
      sessionStorage.setItem("admin-authenticated", "true");
      sessionStorage.setItem("admin-display-name", result.displayName);
      sessionStorage.setItem("admin-tenant-name", tenantCode);
      await client.get("/auth/csrf");
    },
    async logout() {
      if (this.authenticated) await client.post("/auth/logout");
      this.clear();
    },
    clear() {
      this.displayName = "";
      this.tenantName = "";
      this.authenticated = false;
      sessionStorage.removeItem("admin-authenticated");
      sessionStorage.removeItem("admin-display-name");
      sessionStorage.removeItem("admin-tenant-name");
    }
  }
});
