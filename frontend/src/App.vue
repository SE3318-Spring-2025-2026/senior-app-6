<script setup lang="ts">
	import { ref } from "vue";

	const message = ref("");
	const randomString = ref("");
	const greeting = ref("");

	async function fetchMessage() {
		const response = await fetch("http://localhost:8080/api/test/hello");
		message.value = await response.text();
	}

	async function fetchRandomString() {
		const response = await fetch("http://localhost:8080/api/test/random");
		randomString.value = await response.text();
	}

	async function fetchGreeting() {
		const response = await fetch("http://localhost:8080/api/test/greet?name=Vue");
		greeting.value = await response.text();
	}

	fetchMessage();
	fetchRandomString();
	fetchGreeting();
</script>

<template>
	<main>
		<h1>Welcome to Vue!</h1>
		<p>{{ message }}</p>
		<p>{{ randomString }}</p>
		<p>{{ greeting }}</p>
	</main>
</template>

<style>
	body {
		margin-left: 1rem;
		background: #111;
		color: #fff;
	}
</style>

<style scoped>
	main {
		padding: 1rem;
		font-family: "Roboto Mono", monospace;
	}
</style>
