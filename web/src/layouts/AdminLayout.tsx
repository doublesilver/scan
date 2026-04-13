import { Outlet, useLocation } from "react-router-dom";
import TopBar from "../components/TopBar";

export default function AdminLayout() {
  const location = useLocation();
  const isCatalog =
    location.pathname === "/catalog" || location.pathname === "/";

  return (
    <div className="admin-layout">
      <TopBar />
      <main className={`admin-content${isCatalog ? " with-sidebar" : ""}`}>
        <Outlet />
      </main>
    </div>
  );
}
