// Suspense fallback shown while a lazy-loaded route chunk is downloading.
export default function PageLoader() {
    return (
        <div className="flex-center" style={{ minHeight: '100vh' }}>
            <div className="loading-spinner" />
        </div>
    );
}
