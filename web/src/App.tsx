import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import AdminLayout from "./layouts/AdminLayout";
import CatalogPage from "./pages/CatalogPage";
import BoxesPage from "./pages/BoxesPage";
import CartPage from "./pages/CartPage";
import MapPage from "./pages/MapPage";
import DataPage from "./pages/DataPage";
import SettingsPage from "./pages/SettingsPage";
import PrintPage from "./pages/PrintPage";

function App() {
  return (
    <BrowserRouter basename="/admin">
      <Routes>
        <Route element={<AdminLayout />}>
          <Route index element={<Navigate to="/catalog" replace />} />
          <Route path="catalog" element={<CatalogPage />} />
          <Route path="boxes" element={<BoxesPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route path="map" element={<MapPage />} />
          <Route path="data" element={<DataPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="print" element={<PrintPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
