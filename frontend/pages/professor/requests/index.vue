<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between border-b border-gray-200 pb-4 dark:border-gray-800">
      <h1 class="text-2xl font-bold text-gray-900 dark:text-white">Advisor Requests Inbox</h1>
      <span v-if="requests.length > 0" class="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
        {{ requests.length }} Pending
      </span>
    </div>

    <!-- Error State -->
    <div v-if="error" class="rounded-lg bg-red-50 p-4 text-sm text-red-600 dark:bg-red-900/50 dark:text-red-200">
      {{ error }}
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="flex justify-center py-12">
      <div class="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent"></div>
    </div>

    <!-- Empty State -->
    <div v-else-if="requests.length === 0" class="flex flex-col items-center justify-center rounded-xl border border-dashed border-gray-200 bg-gray-50 py-24 text-center dark:border-gray-800 dark:bg-gray-900">
      <svg class="h-12 w-12 text-gray-400 mb-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 8l7.89 5.26a2 2 0 002.22 0L21 8M5 19h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
      </svg>
      <h3 class="mt-2 text-lg font-medium text-gray-900 dark:text-white">No pending requests</h3>
      <p class="mt-1 text-sm text-gray-500 dark:text-gray-400">You don't have any pending advisor requests right now.</p>
    </div>

    <!-- Requests Grid -->
    <div v-else class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="request in requests" :key="request.requestId" class="flex flex-col overflow-hidden rounded-xl border border-gray-200 bg-white shadow-sm transition-shadow hover:shadow-md dark:border-gray-800 dark:bg-gray-900">
        <div class="flex-1 p-6">
          <div class="flex items-center justify-between">
            <span class="inline-flex items-center rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-800 dark:bg-gray-800 dark:text-gray-300">
              {{ request.memberCount }} members
            </span>
            <span class="text-xs text-gray-500 dark:text-gray-400">{{ formatDate(request.sentAt) }}</span>
          </div>
          
          <h3 class="mt-4 text-lg font-bold text-gray-900 dark:text-white">
            {{ request.groupName }}
          </h3>
          
          <div class="mt-2 text-sm text-gray-500 dark:text-gray-400">
            Term: {{ formatTermId(request.termId) }}
          </div>
        </div>
        
        <div class="border-t border-gray-200 p-4 dark:border-gray-800">
          <button 
            @click="openDetail(request.requestId)"
            class="flex w-full items-center justify-center rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-medium text-gray-900 hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-gray-200 dark:border-gray-700 dark:bg-gray-800 dark:text-white dark:hover:bg-gray-700"
          >
            Review Request
          </button>
        </div>
      </div>
    </div>

    <!-- Request Detail Modal -->
    <AdvisorRequestModal
      :is-open="isModalOpen"
      :request-id="selectedRequestId"
      @close="isModalOpen = false"
      @responded="handleResponse"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useAuthStore } from '~/stores/auth';
import type { AdvisorRequestItem } from '~/types/advisor';
import AdvisorRequestModal from '~/components/AdvisorRequestModal.vue';

// Use same layout as other authenticated pages if they exist
definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const authStore = useAuthStore();
const requests = ref<AdvisorRequestItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);

// Modal state
const isModalOpen = ref(false);
const selectedRequestId = ref<string | null>(null);

const fetchRequests = async () => {
  loading.value = true;
  error.value = null;
  
  try {
    const config = useRuntimeConfig();
    const API_URL = config.public.apiBaseUrl || 'http://localhost:8080';
    
    const response = await $fetch<AdvisorRequestItem[]>('/api/advisor/requests', {
      baseURL: API_URL,
      headers: {
        Authorization: `Bearer ${authStore.token}`
      }
    });
    
    requests.value = response;
  } catch (e: any) {
    console.error('Error fetching requests:', e);
    error.value = 'Failed to load advisor requests. ' + (e.data?.error || '');
    requests.value = [];
  } finally {
    loading.value = false;
  }
};

const openDetail = (id: string) => {
  selectedRequestId.value = id;
  isModalOpen.value = true;
};

// Remove request from local state when responded
const handleResponse = (requestId: string, accept: boolean) => {
  requests.value = requests.value.filter(r => r.requestId !== requestId);
  // Optional visually show toast depending on accept value here
};

const formatTermId = (termId: string) => {
  if (!termId) return '';
  // Format "spring-2025-abc" to "Spring 2025"
  const parts = termId.split('-');
  if (parts.length >= 2) {
    return `${parts[0].charAt(0).toUpperCase() + parts[0].slice(1)} ${parts[1]}`;
  }
  return termId;
};

const formatDate = (dateString: string) => {
  if (!dateString) return '';
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short', 
    day: 'numeric', 
    year: 'numeric'
  });
};

onMounted(() => {
  fetchRequests();
});
</script>

