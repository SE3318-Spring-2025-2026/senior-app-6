<template>
  <div v-if="isOpen" class="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto overflow-x-hidden bg-black/50 p-4">
    <div class="relative w-full max-w-lg rounded-xl bg-white shadow-xl dark:bg-slate-900">
      <!-- Modal Header -->
      <div class="flex items-center justify-between border-b border-slate-200 p-4 dark:border-slate-800">
        <h3 class="text-lg font-semibold text-slate-900 dark:text-white">
          Request Details
        </h3>
        <button @click="closeModal" class="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white">
          <svg class="h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- Modal Body -->
      <div class="p-6">
        <div v-if="loading" class="flex justify-center py-8">
          <div class="h-8 w-8 animate-spin rounded-full border-4 border-blue-600 border-t-transparent"></div>
        </div>

        <div v-else-if="error" class="rounded-lg bg-red-50 p-4 text-sm text-red-600 dark:bg-red-900/50 dark:text-red-200">
          {{ error }}
        </div>

        <div v-else-if="requestDetail" class="space-y-6">
          <!-- Group Info -->
          <div>
            <h4 class="mb-2 font-medium text-slate-900 dark:text-white">Group Information</h4>
            <div class="grid grid-cols-2 gap-4 rounded-lg bg-slate-50 p-4 text-sm dark:bg-slate-800/50">
              <div>
                <span class="block text-slate-500 dark:text-slate-400">Name</span>
                <span class="font-medium text-slate-900 dark:text-white">{{ requestDetail.group.groupName }}</span>
              </div>
              <div>
                <span class="block text-slate-500 dark:text-slate-400">Status</span>
                <span class="inline-flex items-center rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-medium text-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
                  {{ requestDetail.group.status }}
                </span>
              </div>
              <div>
                <span class="block text-slate-500 dark:text-slate-400">Sent At</span>
                <span class="font-medium text-slate-900 dark:text-white">{{ formatDate(requestDetail.sentAt) }}</span>
              </div>
            </div>
          </div>

          <!-- Members List -->
          <div>
            <h4 class="mb-2 font-medium text-slate-900 dark:text-white">Members ({{ requestDetail.group.members.length }})</h4>
            <div class="divide-y divide-slate-200 overflow-hidden rounded-lg border border-slate-200 dark:divide-slate-800 dark:border-slate-800">
              <div v-for="member in requestDetail.group.members" :key="member.studentId" class="flex items-center justify-between bg-white p-3 dark:bg-slate-900">
                <span class="text-sm font-medium text-slate-900 dark:text-slate-200">{{ member.studentId }}</span>
                <span :class="[
                  'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
                  member.role === 'TEAM_LEADER' ? 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300' : 'bg-slate-100 text-slate-800 dark:bg-slate-800 dark:text-slate-300'
                ]">
                  {{ member.role === 'TEAM_LEADER' ? 'Leader' : 'Member' }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Modal Footer -->
      <div v-if="!loading && requestDetail && !error" class="flex items-center justify-end space-x-3 border-t border-slate-200 p-4 dark:border-slate-800">
        <button 
          @click="respond(false)" 
          :disabled="processing"
          class="rounded-lg border border-slate-200 bg-white px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-slate-200 disabled:opacity-50 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
        >
          Decline
        </button>
        <button 
          @click="respond(true)" 
          :disabled="processing"
          class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
        >
          {{ processing ? 'Processing...' : 'Accept Request' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import type { AdvisorRequestDetail, AdvisorRespondRequest, AdvisorRespondResponse } from '~/types/advisor';
import { useAuthStore } from '~/stores/auth';

const props = defineProps<{
  isOpen: boolean;
  requestId: string | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'responded', requestId: string, accept: boolean): void;
}>();

const authStore = useAuthStore();
const requestDetail = ref<AdvisorRequestDetail | null>(null);
const loading = ref(false);
const processing = ref(false);
const error = ref<string | null>(null);

const fetchDetail = async () => {
  if (!props.requestId) return;
  
  loading.value = true;
  error.value = null;
  requestDetail.value = null;
  
  try {
    const config = useRuntimeConfig();
    const API_URL = config.public.apiBaseUrl || 'http://localhost:8080';
    
    // We are not using useApiClient here to easily catch errors since the error type is unboxed or thrown directly
    const response = await $fetch<AdvisorRequestDetail>(`/api/advisor/requests/${props.requestId}`, {
      baseURL: API_URL,
      headers: {
        Authorization: `Bearer ${authStore.token}`
      }
    });
    
    requestDetail.value = response;
  } catch (e: any) {
    console.error('Error fetching detail:', e);
    error.value = e.data?.error || 'Failed to load request details';
  } finally {
    loading.value = false;
  }
};

const respond = async (accept: boolean) => {
  if (!props.requestId) return;
  
  processing.value = true;
  error.value = null;
  
  try {
    const config = useRuntimeConfig();
    const API_URL = config.public.apiBaseUrl || 'http://localhost:8080';
    
    await $fetch<AdvisorRespondResponse>(`/api/advisor/requests/${props.requestId}/respond`, {
      method: 'PATCH',
      baseURL: API_URL,
      headers: {
        Authorization: `Bearer ${authStore.token}`
      },
      body: { accept } as AdvisorRespondRequest
    });
    
    emit('responded', props.requestId, accept);
    closeModal();
  } catch (e: any) {
    console.error('Error responding to request:', e);
    // Capacity guard handling
    if (e.status === 400 && e.data?.error?.includes('capacity')) {
      error.value = "You have reached maximum capacity.";
    } else {
      error.value = e.data?.error || 'An error occurred while processing the request.';
    }
  } finally {
    processing.value = false;
  }
};

const closeModal = () => {
  emit('close');
};

const formatDate = (dateString: string) => {
  if (!dateString) return '';
  return new Date(dateString).toLocaleDateString('en-US', {
    month: 'short', 
    day: 'numeric', 
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  });
};

watch(() => props.isOpen, (newVal) => {
  if (newVal && props.requestId) {
    fetchDetail();
  } else {
    requestDetail.value = null;
    error.value = null;
  }
});
</script>