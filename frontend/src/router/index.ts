import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
		{ path: '/home', name: 'home', component: () => import('../views/HomePage.vue') },
		{ path: "/staff-login", name: 'staff-login', component: () => import('../views/StaffLogin.vue') },
		{ path: "/student-login", name: 'student-login', component: () => import('../views/StudentLogin.vue') },
		{ path: "/github-oauth-callback", name: 'github-oauth-callback', component: () => import('../GitHubOAuthCallback.vue') },
		{ path: "/:pathMatch(.*)*", redirect: '/home' }
	],
})

export default router
