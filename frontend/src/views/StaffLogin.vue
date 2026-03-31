<script setup lang="ts">

import { useAuthStore } from "@/stores/auth";
import { ref } from "vue";
import { useRouter } from "vue-router";

const router = useRouter();
const isLoginSuccessful = ref<boolean | null>(null);

async function handleSubmit(event: Event) {
	event.preventDefault();
	const form = event.target as HTMLFormElement;
	const formData = new FormData(form);
	const email = formData.get("email") as string;
	const password = formData.get("password") as string;

	try {
		const response = await fetch("http://localhost:8080/api/auth/login", {
			method: "POST",
			headers: {
				"Content-Type": "application/json",
			},
			body: JSON.stringify({ mail: email, password }),
		});

		if (!response.ok) {
			isLoginSuccessful.value = false;
			return;
		}

		const data: LoginResponse = await response.json();
		console.log("Login response:", data);

		useAuthStore().login(data.token, {
			userType: "Staff",
			id: data.userInfo.id,
			mail: data.userInfo.mail,
			role: data.userInfo.role,
			firstLogin: data.userInfo.firstLogin
		} as User);
		console.log("User info stored in auth store:", useAuthStore().userInfo);
		router.push("/home");
	} catch (error) {
		isLoginSuccessful.value = false;
		console.error("Login failed:", error);
	}
}

</script>

<template>
	<main>
		<form @submit.prevent="handleSubmit">
			<div id="form-container">
				<div>
					<label for="email">Email</label>
					<input type="email" id="email" name="email" required />
				</div>
				<div>
					<label for="password">Password</label>
					<input type="password" id="password" name="password" required />
				</div>
				<button type="submit">Login</button>
			</div>
		</form>
		<p v-if="isLoginSuccessful === false" style="color: red;">Login failed. Please check your credentials and try again.</p>
	</main>
</template>

<style scoped>
	#form-container {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		max-width: 300px;
	}

	main {
		display: flex;
		flex-direction: column;
		align-items: center;
		margin-top: 2rem;
	}
</style>
