export interface GithubLoginRequest {
  code: string;
  studentId: string;
}

export interface GithubLoginResponse {
  token: string;
  userInfo: {
    id: string;
    githubUsername: string;
    role: string;
  };
}

export interface LoginResponse {
	token: string;
	userInfo: {
		id: string;
		mail: string;
		role: 'Admin' | 'Coordinator' | 'Professor';
		firstLogin: boolean;
	};
}
