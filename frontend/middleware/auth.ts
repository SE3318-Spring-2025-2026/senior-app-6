import { useAuthStore } from '~/stores/auth';

/**
 * Route middleware that enforces role-based access control.
 *
 * Pages define their allowed role(s) via route meta:
 *   definePageMeta({ middleware: 'auth', meta: { roles: ['Admin'] } })
 *
 * - Unauthenticated users → /auth/login
 * - Authenticated users without a matching role → /forbidden
 */
export default defineNuxtRouteMiddleware((to) => {
  const authStore = useAuthStore();

  if (!authStore.isAuthenticated || !authStore.userInfo) {
    return navigateTo('/auth/login');
  }

  const allowedRoles = (to.meta as Record<string, unknown>).roles as string[] | undefined;

  if (allowedRoles && allowedRoles.length > 0) {
    const userRole = authStore.userInfo.userType === 'Staff'
      ? (authStore.userInfo as StaffUser).role
      : 'Student';

    if (!allowedRoles.includes(userRole)) {
      return navigateTo('/forbidden');
    }
  }
});
