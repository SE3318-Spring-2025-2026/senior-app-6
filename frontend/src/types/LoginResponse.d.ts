interface LoginResponse {
		token: string;
		userInfo: {
				id: string;
				mail: string;
				role: "Admin" | "Coordinator" | "Professor";
				firstLogin: boolean;
		};
}
