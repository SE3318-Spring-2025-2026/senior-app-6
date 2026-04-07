import { useAuthStore } from '~/stores/auth';

export default defineNuxtRouteMiddleware(() => {
  const authStore = useAuthStore();

  if (authStore.isAuthenticated && authStore.userInfo) {
    return navigateTo(`/${authStore.userInfo.role.toLowerCase()}/dashboard`);
  }
});
