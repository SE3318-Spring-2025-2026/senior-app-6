export interface StaffUser {
	readonly userType: 'Staff';
	id: string;
	mail: string;
	role: 'Admin' | 'Coordinator' | 'Professor';
	firstLogin: boolean;
}

export interface StudentUser {
	readonly userType: 'Student';
	id: string;
	studentId: string;
	githubUsername: string | null;
	readonly role: 'Student';
}

export type User = StaffUser | StudentUser;
