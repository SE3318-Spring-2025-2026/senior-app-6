export type WindowType = 'GROUP_CREATION' | 'ADVISOR_ASSOCIATION';

export interface ScheduleWindowItem {
  id: string | null;
  type: WindowType;
  termId: string;
  opensAt: string | null;
  closesAt: string | null;
  isActive: boolean;
}

export interface ScheduleWindowPayload {
  type: WindowType;
  opensAt: string;
  closesAt: string;
}
