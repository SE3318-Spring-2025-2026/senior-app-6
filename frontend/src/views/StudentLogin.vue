<script setup lang="ts">

async function handleSubmit(event: Event) {
	event.preventDefault();
	const form = event.target as HTMLFormElement;
	const formData = new FormData(form);
	const studentId = formData.get("studentId") as string;
	console.log("Student ID:", studentId);
}

</script>

<template>
	<main>
			<form @submit="handleSubmit">
				<div id="form-container">
					<label for="studentId">Student ID</label>
					<input type="text" pattern="[0-9]{11}" maxlength="11" id="studentId" name="studentId" required />
					<button type="submit">Login via Github</button>
				</div>
			</form>
	</main>
</template>

<style scoped>
	#form-container {
		display: flex;
		flex-direction: column;
		gap: 1rem;
		align-items: center;
	}

	button {
		margin-top: 1rem;
	}
</style>
