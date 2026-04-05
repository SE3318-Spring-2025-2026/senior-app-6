// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: "2024-11-01",
  devtools: { enabled: true },

  modules: ["@pinia/nuxt", "@nuxtjs/tailwindcss", "@nuxtjs/color-mode"],

  colorMode: {
    classSuffix: "",
    preference: "dark",
    fallback: "dark",
  },

  css: ["~/assets/css/main.css"],

  runtimeConfig: {
    public: {
      apiBaseUrl: process.env.NUXT_PUBLIC_API_BASE_URL || "http://localhost:8080/api",
      githubClientId: process.env.NUXT_PUBLIC_GITHUB_CLIENT_ID || "",
      githubRedirectUri: process.env.NUXT_PUBLIC_GITHUB_REDIRECT_URI || "http://localhost:3000/auth/github-callback",
    },
  },

  typescript: {
    strict: true,
  },
});
