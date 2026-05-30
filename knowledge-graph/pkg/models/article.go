// Package models defines data structures for the TechPulse VN pipeline
package models

import (
	"time"
)

// RawArticle represents an article from crawlers (raw_articles topic)
type RawArticle struct {
	MessageType    string          `json:"message_type"`
	SourcePlatform string          `json:"source_platform"`
	CrawledAt      time.Time       `json:"crawled_at"`
	Data           ArticleData     `json:"data"`
}

// ArticleData holds the actual article content
type ArticleData struct {
	Title       string `json:"title"`
	PublishDate string `json:"publish_date"`
	Content     string `json:"content"`
	SourceURL   string `json:"source_url"`
}

// RawJob represents a job posting from crawlers (raw_jobs topic)
type RawJob struct {
	MessageType    string    `json:"message_type"`
	SourcePlatform string    `json:"source_platform"`
	CrawledAt      time.Time `json:"crawled_at"`
	Data           JobData   `json:"data"`
}

// JobData holds the actual job posting content
type JobData struct {
	JobTitle     string   `json:"job_title"`
	CompanyName  string   `json:"company_name"`
	Location     string   `json:"location"`
	Salary       string   `json:"salary"`
	Level        string   `json:"level"`
	Description  string   `json:"description"`
	Requirement  string   `json:"requirement"`
	Benefit      string   `json:"benefit"`
	Skills       []string `json:"skills"`
	SourceURL    string   `json:"source_url"`
	PostedDate   string   `json:"posted_date"`
}

// ExtractedArticle represents an article with extracted entities
type ExtractedArticle struct {
	MessageType    string         `json:"message_type"`
	SourcePlatform string         `json:"source_platform"`
	CrawledAt      time.Time      `json:"crawled_at"`
	ExtractedAt    time.Time      `json:"extracted_at"`
	Data           ExtractedArticleData `json:"data"`
}

// ExtractedArticleData holds article data with entities
type ExtractedArticleData struct {
	Title       string            `json:"title"`
	PublishDate string            `json:"publish_date"`
	Content     string            `json:"content"`
	SourceURL   string            `json:"source_url"`
	Entities    Entities          `json:"entities"`
}

// ExtractedJob represents a job with extracted entities
type ExtractedJob struct {
	MessageType    string         `json:"message_type"`
	SourcePlatform string         `json:"source_platform"`
	CrawledAt      time.Time      `json:"crawled_at"`
	ExtractedAt    time.Time      `json:"extracted_at"`
	Data           ExtractedJobData `json:"data"`
}

// ExtractedJobData holds job data with structured information
type ExtractedJobData struct {
	Job          JobInfo     `json:"job"`
	Company      CompanyInfo `json:"company"`
	Skills       []string    `json:"skills"`
	Technologies []string    `json:"technologies"`
	Entities     Entities    `json:"entities"`
}

// JobInfo holds job-specific information
type JobInfo struct {
	Title       string `json:"title"`
	Description string `json:"description"`
	Requirement string `json:"requirement"`
	Benefit     string `json:"benefit"`
	Salary      string `json:"salary"`
	DueDate     string `json:"due_date"`
	SourceURL   string `json:"source_url"`
}

// CompanyInfo holds company-specific information
type CompanyInfo struct {
	Name     string `json:"name"`
	Size     string `json:"size"`
	Field    string `json:"field"`
	Location string `json:"location"`
}

// Entities holds extracted named entities
// Note: PER (Person) field removed - person extraction disabled due to low accuracy
type Entities struct {
	TECH     []string `json:"TECH,omitempty"`
	ORG      []string `json:"ORG,omitempty"`
	LOC      []string `json:"LOC,omitempty"`
	DATE     []string `json:"DATE,omitempty"`
	JOB_ROLE []string `json:"JOB_ROLE,omitempty"`
	SALARY   []string `json:"SALARY,omitempty"`
}

// ArticleVector represents an article embedding vector
type ArticleVector struct {
	MessageType    string            `json:"message_type"`
	ID             string            `json:"id"`
	SourceURL      string            `json:"source_url"`
	SourcePlatform string            `json:"source_platform"`
	Embedding      []float64         `json:"embedding"`
	Metadata       ArticleMetadata   `json:"metadata"`
}

// ArticleMetadata holds metadata for vector storage
type ArticleMetadata struct {
	Title          string `json:"title"`
	PublishedDate  string `json:"published_date"`
}

// JobVector represents a job embedding vector
type JobVector struct {
	MessageType    string        `json:"message_type"`
	ID             string        `json:"id"`
	SourceURL      string        `json:"source_url"`
	SourcePlatform string        `json:"source_platform"`
	Embedding      []float64     `json:"embedding"`
	Metadata       JobMetadata   `json:"metadata"`
}

// JobMetadata holds metadata for job vector storage
type JobMetadata struct {
	Title         string `json:"title"`
	CompanyName   string `json:"company_name"`
	Location      string `json:"location"`
	Salary        string `json:"salary"`
}

// TechnologyCategory maps technology names to categories
var TechnologyCategoryMap = map[string]string{
	// AI/ML models
	"AI": "ai_model", "LLM": "ai_model", "ML": "ai_model", "NLP": "ai_model", 
	"RAG": "ai_model", "Chatbot": "ai_model", "GPT": "ai_model",
	
	// Programming languages
	"Python": "language", "Java": "language", "JavaScript": "language",
	"TypeScript": "language", "Golang": "language", "Go": "language",
	"C++": "language", "C#": "language", "Rust": "language", "PHP": "language",
	
	// Frameworks
	"Django": "framework", "FastAPI": "framework", "Flask": "framework",
	"React": "framework", "Vue": "framework", "Angular": "framework",
	"Spring": "framework", "Express": "framework",
	
	// Tools
	"Docker": "tool", "Git": "tool", "Redis": "tool", "Kafka": "tool",
	"Kubernetes": "tool", "Jenkins": "tool",
	
	// Platforms
	"AWS": "platform", "Azure": "platform", "GCP": "platform",
	"Google Cloud": "platform", "Firebase": "platform",
}

// GetTechnologyCategory returns the category for a technology
func GetTechnologyCategory(tech string) string {
	if cat, ok := TechnologyCategoryMap[tech]; ok {
		return cat
	}
	return "other"
}