// Domain types cho CompanyExplorer/SimilarCompanyPanel/CompanyNeighborhoodGraph.
export interface Company {
    id: string;
    name: string;
    location?: string;
    job_count?: number;
    industry?: string;
    size?: string;
    tech_stack: string[];
}

export interface SimilarCompany {
    id: string;
    name: string;
    location?: string;
    score?: number;
    shared_techs: string[];
}

export interface CompanyMention {
    id: string;
    title: string;
    url: string;
    sourcePlatform?: string;
    publishDate?: string;
}

export interface CompanyInsight {
    summary: string;
    highlights?: string[];
}

export interface CompanyTechHealthScore {
    available: boolean;
    score: number;
    label: string;
    stack_size: number;
    tracked_count: number;
    strengths: string[];
    watch_outs: string[];
}
