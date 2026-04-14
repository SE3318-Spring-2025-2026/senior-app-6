<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { ArrowLeft, Inbox, Clock, Users, ChevronRight, Loader2, AlertCircle } from 'lucide-vue-next';
import { useAuthStore } from '~/stores/auth';
import type { AdvisorRequestItem } from '~/types/advisor';
import AdvisorRequestModal from '~/components/AdvisorRequestModal.vue';

definePageMeta({
  middleware: "auth",
  roles: ["Professor"],
});

const { getAuthToken, fetchAdvisorRequests } = useApiClient();
const authStore = useAuthStore();
const requests = ref<AdvisorRequestItem[]>([]);
const loading = ref(true);
const error = ref<string | null>(null);

// Modal state
const isModalOpen = ref(false);
const selectedRequestId = ref<string | null>(null);

const loadRequests = async () => {
  loading.value = true;
  error.value = null;
  
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required');
    
    requests.value = await fetchAdvisorRequests(token);
  } catch (e: any) {
    console.error('Error fetching requests:', e);
    error.value = e.message || 'Failed to load advisor requests.';
    requests.value = [];
  } finally {
    loading.value = false;
  }
};

const openDetail = (id: string) => {
  selectedRequestId.value = id;
  isModalOpen.value = true;
};

const handleResponse = (requestId: string) => {
  requests.value = requests.value.filter(r => r.requestId !== requestId);
};

const formatTermId = (termId: string) => {
  if (!termId) return '';
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
  loadRequests();
});
</script>

<template>
  <main class="min-h-screen bg-gradient-to-br from-slate-50 via-white to-slate-100 p-4 transition-colors dark:from-slate-950 dark:via-slate-900 dark:to-slate-950 md:p-8">
    <div class="mx-auto w-full max-w-5xl space-y-6">
      <!-- Breadcrumbs / Back Link -->
      <NuxtLink
        to="/professor/dashboard"
        class="inline-flex items-center gap-2 text-sm font-medium text-slate-600 transition hover:text-slate-900 dark:text-slate-400 dark:hover:text-white"
      >
        <ArrowLeft class="h-4 w-4" />
        Back to Dashboard
      </NuxtLink>

      <!-- Header -->
      <header class="rounded-2xl border border-slate-200 bg-white/90 p-6 shadow-sm backdrop-blur transition-colors dark:border-slate-700 dark:bg-slate-800/90 dark:shadow-lg">
        <div class="flex items-center justify-between">
          <div>
            <h1 class="text-2xl font-semibold tracking-tight text-slate-900 dark:text-white md:text-3xl">
              Requests Inbox
            </h1>
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Review and manage incoming advising requests from student groups.
            </p>
          </div>
          <div v-if="!loading && requests.length > 0" class="hidden sm:block">
            <span class="inline-flex items-center rounded-full bg-blue-100 px-3 py-1 text-xs font-medium text-blue-800 dark:bg-blue-900/30 dark:text-blue-300">
              {{ requests.length }} Pending Requests
            </span>
          </div>
        </div>
      </header>

      <!-- Error State -->
      <div v-if="error" class="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-900/20 dark:text-red-300">
        <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
        <p>{{ error }}</p>
      </div>

      <!-- Loading State -->
      <div v-if="loading" class="flex flex-col items-center justify-center py-24">
        <Loader2 class="h-10 w-10 animate-spin text-blue-600 dark:text-blue-400" />
        <p class="mt-4 text-sm font-medium text-slate-600 dark:text-slate-400">Loading your inbox...</p>
      </div>

      <!-- Empty State -->
      <div v-else-if="requests.length === 0" class="flex flex-col items-center justify-center rounded-2xl border border-dashed border-slate-200 bg-white/50 py-24 text-center dark:border-slate-700 dark:bg-slate-900/50">
        <div class="inline-flex h-16 w-16 items-center justify-center rounded-full bg-slate-100 text-slate-400 dark:bg-slate-800 dark:text-slate-500">
          <Inbox class="h-8 w-8" />
        </div>
        <h3 class="mt-4 text-lg font-semibold text-slate-900 dark:text-white">Your inbox is empty</h3>
        <p class="mt-2 text-sm text-slate-600 dark:text-slate-400 max-w-xs mx-auto">
          No student groups have sent you advising requests for the current term yet.
        </p>
      </div>

      <!-- Requests List -->
      <div v-else class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div 
          v-for="request in requests" 
          :key="request.requestId" 
          class="group flex flex-col rounded-2xl border border-slate-200 bg-white shadow-sm transition-all hover:border-blue-300 hover:shadow-md dark:border-slate-700 dark:bg-slate-800 dark:hover:border-blue-600"
        >
          <div class="flex-1 p-6">
            <div class="flex items-center justify-between gap-2">
              <div class="flex items-center gap-1.5 text-xs font-medium text-slate-500 dark:text-slate-400">
                <Users class="h-3.5 w-3.5" />
                {{ request.memberCount }} Members
              </div>
              <div class="flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400">
                <Clock class="h-3.5 w-3.5" />
                {{ formatDate(request.sentAt) }}
              </div>
            </div>
            
            <h3 class="mt-4 text-lg font-semibold text-slate-900 dark:text-white group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
              {{ request.groupName }}
            </h3>
            
            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">
              Term: <span class="font-medium text-slate-700 dark:text-slate-300">{{ formatTermId(request.termId) }}</span>
            </p>
          </div>
          
          <div class="border-t border-slate-100 p-4 dark:border-slate-700/50">
            <button 
              @click="openDetail(request.requestId)"
              class="flex w-full items-center justify-center gap-2 rounded-xl bg-slate-50 px-4 py-2.5 text-sm font-semibold text-slate-900 transition hover:bg-blue-600 hover:text-white dark:bg-slate-900/50 dark:text-white dark:hover:bg-blue-600"
            >
              View Details
              <ChevronRight class="h-4 w-4" />
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
  </main>
</template>
