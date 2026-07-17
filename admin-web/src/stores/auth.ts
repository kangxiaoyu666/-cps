import { defineStore } from "pinia";
export const useAuthStore=defineStore("auth",{state:()=>({authenticated:sessionStorage.getItem("authenticated")==="true"}),actions:{login(){this.authenticated=true;sessionStorage.setItem("authenticated","true");},logout(){this.authenticated=false;sessionStorage.removeItem("authenticated");}}});
