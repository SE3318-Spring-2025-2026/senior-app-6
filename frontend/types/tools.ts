export interface BindJiraRequest {
  jiraSpaceUrl: string;
  jiraEmail: string;
  jiraProjectKey: string;
  jiraApiToken: string;
}

export interface BindGithubRequest {
  githubOrgName: string;
  githubPat: string;
}

import type { GroupStatus } from "./group";

export interface BindToolResponse {
  groupId: string;
  status: GroupStatus;
  jiraSpaceUrl?: string | null;
  jiraProjectKey?: string | null;
  githubOrgName?: string | null;
  jiraBound: boolean;
  githubBound: boolean;
}
