import { BrowserRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { CompaniesPage } from "./pages/CompaniesPage";
import { SitesPage } from "./pages/SitesPage";
import { JobTypesPage } from "./pages/JobTypesPage";
import { SessionsPage } from "./pages/SessionsPage";
import { ReportsPage } from "./pages/ReportsPage";
import { InvoicesPage } from "./pages/InvoicesPage";
import { PlanningPage } from "./pages/PlanningPage";
import { SettingsPage } from "./pages/SettingsPage";

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route element={<ProtectedRoute />}>
            <Route element={<Layout />}>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/companies" element={<CompaniesPage />} />
              <Route path="/sites" element={<SitesPage />} />
              <Route path="/job-types" element={<JobTypesPage />} />
              <Route path="/sessions" element={<SessionsPage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/invoices" element={<InvoicesPage />} />
              <Route path="/planning" element={<PlanningPage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
