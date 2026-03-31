<script setup lang="ts">
import { useAuthStore } from '@/stores/auth';
import { ref } from 'vue';

const authStore = useAuthStore();
if (authStore.isAuthenticated) {
	fetchGreeting(authStore.userInfo?.id || "User");
}

const greetingMessage = ref("");

async function fetchGreeting(name: string) {
	try {
		const response = await fetch(
			`http://localhost:8080/api/test/greeting?name=${name}`,
			{
				headers: {
					"Authorization": "Bearer " + authStore.token,
				},
			}
		);
		if (!response.ok) {
			greetingMessage.value = "Failed to fetch greeting Response: " + response.statusText;
			return;
		}
		greetingMessage.value = await response.text();
	} catch (error) {
		greetingMessage.value = "Failed to fetch greeting: " + error;
	}
}

async function handleLogout() {
	authStore.logout();
	greetingMessage.value = "";
}

</script>

<template>
	<main>
		<h1>Senior Project Management</h1>
		<div id="login-container">
			<router-link to="/staff-login">Login as Staff</router-link>
			<router-link to="/student-login">Login as Student</router-link>
		</div>
		<p v-if="authStore.isAuthenticated" id="greeting-message">{{ greetingMessage }}</p>
		<p v-else id="greeting-message">Please log in to see your personalized greeting.</p>
		<button v-if="authStore.isAuthenticated" @click="handleLogout">Logout</button>
	</main>
</template>

<style scoped>
	#login-container {
		display: flex;
		gap: 2rem;
		justify-content: center;
	}

	#greeting-message {
		margin-top: 2rem;
		white-space: pre-wrap;
		text-align: center;
	}

	a {
		background: #222;
		color: #fff;
		padding: 0.5rem 1rem;
		border-radius: 4px;
		text-decoration: none;
		font-family: var(--font-family);
	}
	a:hover {
		background: #333;
	}

	main {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-top: 2rem;
	}
</style>
