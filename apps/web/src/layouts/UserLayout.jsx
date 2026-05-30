import { Outlet } from 'react-router-dom';
import Header from '../components/layout/Header';
import Footer from '../components/layout/Footer';
import MaintenancePage from '../pages/MaintenancePage';
import { useAppContext } from '../contexts/AppContext';

export default function UserLayout() {
    const { settings } = useAppContext();

    if (settings?.isWebMaintenance) {
        return <MaintenancePage />;
    }

    return (
        <div className="app-layout">
            <Header />
            <main className="page-content">
                <Outlet />
            </main>
            <Footer />
        </div>
    );
}
