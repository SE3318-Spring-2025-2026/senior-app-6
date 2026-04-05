import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAuthStore = defineStore('auth', () => {
	const token = ref<string | null>(null);
	const userInfo = ref<User | null>(null);
	const isAuthenticated = ref(false);
	const tokenKey = 'spm-token';
	const userInfoKey = 'spm-userInfo';

	if (import.meta.client) {
		if (localStorage.getItem(tokenKey) != null) {
			token.value = localStorage.getItem(tokenKey);
			userInfo.value = JSON.parse(localStorage.getItem(userInfoKey) || '{}');
			isAuthenticated.value = true;
		}
	}

	function login(tokenValue: string, userInfoValue: User) {
		token.value = tokenValue;
		userInfo.value = userInfoValue;
		isAuthenticated.value = true;
		localStorage.setItem(tokenKey, tokenValue);
		localStorage.setItem(userInfoKey, JSON.stringify(userInfoValue));
	}

	function logout() {
		token.value = null;
		userInfo.value = null;
		isAuthenticated.value = false;
		localStorage.removeItem(tokenKey);
		localStorage.removeItem(userInfoKey);
	}

	return { token, userInfo, tokenKey, userInfoKey, isAuthenticated, login, logout };
});
