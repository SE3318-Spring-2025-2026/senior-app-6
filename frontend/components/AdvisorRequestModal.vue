<script setup lang="ts">
import { ref, watch } from 'vue';
import { X, Loader2, Info, Users, Check, XCircle, AlertCircle } from 'lucide-vue-next';
import type { AdvisorRequestDetail } from '~/types/advisor';
import GroupStatusBadge from '~/components/ui/GroupStatusBadge.vue';
import MemberList from '~/components/ui/MemberList.vue';

const props = defineProps<{
  isOpen: boolean;
  requestId: string | null;
}>();

const emit = defineEmits<{
  (e: 'close'): void;
  (e: 'responded', requestId: string, accept: boolean): void;
}>();

const { getAuthToken, fetchAdvisorRequestDetail, respondToAdvisorRequest } = useApiClient();
const requestDetail = ref<AdvisorRequestDetail | null>(null);
const loading = ref(false);
const processing = ref(false);
const processingAccept = ref(false);
const processingDecline = ref(false);
const error = ref<string | null>(null);

const confirmDecline = () => {
  if (confirm("Are you sure you want to decline this request?")) {
    respond(false);
  }
};

const fetchDetail = async () => {
  if (!props.requestId) return;
  
  loading.value = true;
  error.value = null;
  requestDetail.value = null;
  
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required');
    
    requestDetail.value = await fetchAdvisorRequestDetail(props.requestId, token);
  } catch (e: any) {
    console.error('Error fetching detail:', e);
    error.value = e.message || 'Failed to load request details';
  } finally {
    loading.value = false;
  }
};

const respond = async (accept: boolean) => {
  if (!props.requestId) return;
  
  processing.value = true;
  if (accept) processingAccept.value = true;
  else processingDecline.value = true;
  error.value = null;
  
  try {
    const token = getAuthToken();
    if (!token) throw new Error('Authentication required');
    
    await respondToAdvisorRequest(props.requestId, accept, token);

    emit('responded', props.requestId, accept);
    emit('close');
  } catch (e: any) {
    console.error('Error responding to request:', e);
    if (e.status === 400 && e.message?.toLowerCase().includes('capacity')) {
      error.value = "You have reached your maximum advising capacity.";
    } else {
      error.value = e.message || 'An error occurred while processing the request.';
    }
  } finally {
    processing.value = false;
    processingAccept.value = false;
    processingDecline.value = false;
  }
};

const closeModal = () => {
  if (!processing.value) {
    emit('close');
  }
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
  } else if (!newVal) {
    requestDetail.value = null;
    error.value = null;
  }
});
</script>

<template>
  <div 
    v-if="isOpen" 
    class="fixed inset-0 z-50 flex items-center justify-center p-4 sm:p-6"
    role="dialog"
    aria-modal="true"
  >
    <!-- Backdrop -->
    <div 
      class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity" 
      @click="closeModal"
    ></div>

    <!-- Modal Content -->
    <div class="relative w-full max-w-lg overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-2xl transition-all dark:border-slate-700 dark:bg-slate-900">
      <!-- Header -->
      <div class="flex items-center justify-between border-b border-slate-100 p-6 dark:border-slate-800">
        <div class="flex items-center gap-3">
          <div class="inline-flex h-10 w-10 items-center justify-center rounded-xl bg-blue-50 text-blue-600 dark:bg-blue-950/50 dark:text-blue-400">
            <Info class="h-5 w-5" />
          </div>
          <div>
            <h3 class="text-xl font-semibold text-slate-900 dark:text-white">
              Request Details
            </h3>
            <p v-if="requestDetail" class="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
              Sent on {{ formatDate(requestDetail.sentAt) }}
            </p>
          </div>
        </div>
        <button 
          @click="closeModal" 
          class="rounded-xl p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-800 dark:hover:text-white"
          :disabled="processing"
        >
          <X class="h-5 w-5" />
        </button>
      </div>

      <!-- Body -->
      <div class="max-h-[70vh] overflow-y-auto p-6">
        <!-- Loading State -->
        <div v-if="loading" class="flex flex-col items-center justify-center py-12">
          <Loader2 class="h-10 w-10 animate-spin text-blue-600 dark:text-blue-400" />
          <p class="mt-4 text-sm font-medium text-slate-600 dark:text-slate-400">Fetching details...</p>
        </div>

        <!-- Error State -->
        <div v-else-if="error" class="flex items-start gap-3 rounded-2xl border border-red-200 bg-red-50 p-4 text-sm text-red-700 dark:border-red-900/50 dark:bg-red-900/20 dark:text-red-300">
          <AlertCircle class="mt-0.5 h-5 w-5 shrink-0" />
          <p>{{ error }}</p>
        </div>

        <div v-else-if="requestDetail" class="space-y-8">
          <!-- Group Info Card -->
          <section>
            <div class="mb-3 flex items-center gap-2">
              <Users class="h-4 w-4 text-slate-400" />
              <h4 class="text-sm font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Group Info</h4>
            </div>
            <div class="rounded-2xl border border-slate-100 bg-slate-50/50 p-5 dark:border-slate-800 dark:bg-slate-800/40">
              <div class="flex flex-wrap items-center justify-between gap-4">
                <div>
                  <span class="block text-xs font-medium text-slate-500 dark:text-slate-500">GROUP NAME</span>
                  <span class="text-lg font-bold text-slate-900 dark:text-white">{{ requestDetail.group.groupName }}</span>
                </div>
                <div class="flex flex-col items-end">
                  <span class="block text-xs font-medium text-slate-500 dark:text-slate-500 mb-1">STATUS</span>
                  <GroupStatusBadge :status="requestDetail.group.status" />
                </div>
              </div>
            </div>
          </section>

          <!-- Members Section -->
          <section>
            <div class="mb-3 flex items-center justify-between">
              <div class="flex items-center gap-2">
                <Users class="h-4 w-4 text-slate-400" />
                <h4 class="text-sm font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Group Members</h4>
              </div>
              <span class="rounded-full bg-slate-100 px-2 py-0.5 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-400">
                {{ requestDetail.group.members.length }}
              </span>
            </div>
            
            <div class="overflow-hidden rounded-2xl border border-slate-100 dark:border-slate-800">
              <MemberList :members="requestDetail.group.members" />
            </div>
          </section>
        </div>
      </div>

      <!-- Footer -->
      <div v-if="!loading && requestDetail" class="grid grid-cols-2 gap-3 border-t border-slate-100 p-6 dark:border-slate-800">
        <button 
          @click="confirmDecline" 
          :disabled="processing"
          class="flex items-center justify-center gap-2 rounded-xl border border-red-200 bg-white px-4 py-3 text-sm font-bold text-red-600 transition hover:bg-red-50 disabled:opacity-50 dark:border-red-900/30 dark:bg-slate-900 dark:text-red-400 dark:hover:bg-red-900/20"
        >
          <XCircle v-if="!processingDecline" class="h-4 w-4" />
          <Loader2 v-else class="h-4 w-4 animate-spin" />
          {{ processingDecline ? 'Declining...' : 'Decline' }}
        </button>
        <button 
          @click="respond(true)" 
          :disabled="processing"
          class="flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-bold text-white shadow-lg shadow-blue-500/20 transition hover:bg-blue-700 hover:shadow-blue-500/40 disabled:opacity-50 dark:bg-blue-500 dark:hover:bg-blue-600"
        >
          <Check v-if="!processingAccept" class="h-4 w-4" />
          <Loader2 v-else class="h-4 w-4 animate-spin" />
          {{ processingAccept ? 'Accepting...' : 'Accept' }}
        </button>
      </div>
    </div>
  </div>
</template>
