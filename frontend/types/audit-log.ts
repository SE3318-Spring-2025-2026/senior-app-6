export interface AuditLogEntry {
  id: string;
  userId: string | null;
  userType: string;
  category: string;
  action: string;
  outcome: string;
  ipAddress: string | null;
  occurredAt: string;
}

export interface PagedAuditLogResponse {
  content: AuditLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLogQuery {
  category?: string;
  outcome?: string;
  userType?: string;
  userId?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}
