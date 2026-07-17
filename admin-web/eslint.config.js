import js from "@eslint/js";
import globals from "globals";
import tseslint from "typescript-eslint";
import pluginVue from "eslint-plugin-vue";
export default tseslint.config(js.configs.recommended,...tseslint.configs.recommended,...pluginVue.configs["flat/recommended"],{languageOptions:{globals:globals.browser,parserOptions:{parser:tseslint.parser}},rules:{"vue/multi-word-component-names":"off"}},{ignores:["dist/**"]});
