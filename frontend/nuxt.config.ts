// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: "2024-11-01",
  devtools: { enabled: true },

  modules: ["@pinia/nuxt", "@nuxtjs/tailwindcss", 'pinia-plugin-persistedstate/nuxt', "@nuxtjs/color-mode"],

  colorMode: {
    classSuffix: "",
    preference: "dark",
    fallback: "dark",
  },

  css: ["~/assets/css/main.css"],

  runtimeConfig: {
    public: {
      apiBaseUrl: "http://localhost:8080/api",
      githubClientId: "",
      githubRedirectUri: "http://localhost:3000/auth/github-callback",
    },
  },

  typescript: {
    strict: true,
  },
});
