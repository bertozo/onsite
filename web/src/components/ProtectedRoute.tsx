import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { FullPageSpinner } from "./ui";

export function ProtectedRoute() {
  const { state } = useAuth();
  if (state.status === "checking") return <FullPageSpinner />;
  if (state.status !== "loggedIn") return <Navigate to="/login" replace />;
  return <Outlet />;
}
