interface StaffUser {
		readonly userType: "Staff";
		id: string;
		mail: string;
		role: "Admin" | "Coordinator" | "Professor";
		firstLogin: boolean;
}

interface StudentUser {
		readonly userType: "Student";
		id: string;
		studentId: string;
		githubUsername: string | null;
}

type User = StaffUser | StudentUser;
