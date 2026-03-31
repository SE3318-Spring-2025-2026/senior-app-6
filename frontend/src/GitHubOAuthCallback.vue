<script setup lang="ts">

import { useRoute } from 'vue-router';

const route = useRoute();
console.log("Route query parameters:", route.query);

</script>
