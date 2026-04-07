import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAuthStore = defineStore('auth', () => {
	const token = ref<string | null>(null);
	const userInfo = ref<User | null>(null);
	const isAuthenticated = ref(false);

	function login(tokenValue: string, userInfoValue: User) {
		token.value = tokenValue;
		userInfo.value = userInfoValue;
		isAuthenticated.value = true;
	}

	function logout() {
		token.value = null;
		userInfo.value = null;
		isAuthenticated.value = false;
	}

	return { token, userInfo, isAuthenticated, login, logout };
}, {
	persist: true
});
