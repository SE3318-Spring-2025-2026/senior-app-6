
<template>
	<router-view />
</template>

<style>
	:root {
		--font-family: "Roboto Mono", monospace;
		--element-bg: #222;
		--element-hover-bg: #333;
		--element-color: aliceblue;
	}

	html, body {
		height: 100%;
		margin: 0;
		padding: 0;
	}
	body {
		display: flex;
		justify-content: center;
		/* align-items: center; */
		background: #111;
		color: var(--element-color);
		font-family: var(--font-family);
	}

	main{
		margin: 2rem;
		padding: 2rem;
	}

	label {
		display: block;
		margin-bottom: 0.5rem;
	}

	input {
		background: var(--element-bg);
		font-family: var(--font-family);
		font-size: medium;
		border: 1px solid #333;
		color: var(--element-color);
		padding: 0.5rem;
		border-radius: 4px;
	}

	button {
		background: var(--element-bg);
		border: none;
		font-size: medium;
		color: var(--element-color);
		font-family: var(--font-family);
		padding: 0.5rem 1rem;
		border-radius: 4px;
		cursor: pointer;
	}

	button:hover {
		background: var(--element-hover-bg);
	}

</style>
