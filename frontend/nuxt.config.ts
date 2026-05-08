// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
  compatibilityDate: "2024-11-01",
  devtools: { enabled: true },

  app: {
    head: {
      title: "SPM — Senior Project Management",
      meta: [
        { charset: "utf-8" },
        { name: "viewport", content: "width=device-width, initial-scale=1" },
        { name: "description", content: "University capstone project management platform" },
        { name: "theme-color", content: "#2563eb" },
      ],
      link: [
        { rel: "icon", type: "image/svg+xml", href: "/favicon.svg" },
        { rel: "manifest", href: "/site.webmanifest" },
        { rel: "apple-touch-icon", href: "/favicon.svg" },
      ],
    },
  },

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
