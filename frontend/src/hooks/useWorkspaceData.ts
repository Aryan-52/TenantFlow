import { useEffect, useState } from 'react';
import client from '../api/client';
import { projectApi, type Project } from '../api/work';
import type { Member } from '../pages/Members';

interface Loaded {
  tenantId: string;
  members: Member[];
  projects: Project[];
}

const EMPTY: Member[] = [];
const NO_PROJECTS: Project[] = [];

/**
 * Members and projects of one workspace for filters and pickers. Results are tagged with
 * the workspace they belong to and ignored for any other, so a picker never offers another
 * workspace's people or projects. Failures leave the lists empty: pickers are optional helpers.
 */
export function useWorkspaceData(tenantId: string | undefined) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);

  useEffect(() => {
    if (!tenantId) return;
    const controller = new AbortController();
    const members = client
      .get<Member[]>(`/api/tenants/${tenantId}/members`, { signal: controller.signal })
      .then((r) => r.data)
      .catch(() => EMPTY);
    const projects = projectApi.list(tenantId).catch(() => NO_PROJECTS);
    Promise.all([members, projects]).then(([m, p]) => {
      if (!controller.signal.aborted) setLoaded({ tenantId, members: m, projects: p });
    });
    return () => controller.abort();
  }, [tenantId]);

  const current = loaded && loaded.tenantId === tenantId ? loaded : null;
  return { members: current?.members ?? EMPTY, projects: current?.projects ?? NO_PROJECTS };
}
