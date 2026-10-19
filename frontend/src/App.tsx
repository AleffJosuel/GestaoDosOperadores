import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./auth/AuthContext";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/LoginPage";
import { DashboardPage } from "./pages/DashboardPage";
import { MapaPage } from "./pages/MapaPage";
import { ColaboradoresPage } from "./pages/ColaboradoresPage";
import { CompetenciasPage } from "./pages/CompetenciasPage";
import { PdiPage } from "./pages/PdiPage";
import { FichaPage } from "./pages/FichaPage";
import { ConfiguracoesPage } from "./pages/ConfiguracoesPage";

export function App() {
  const { autenticado } = useAuth();

  return (
    <Routes>
      <Route
        path="/login"
        element={autenticado ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route element={autenticado ? <Layout /> : <Navigate to="/login" replace />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/mapa" element={<MapaPage />} />
        <Route path="/colaboradores" element={<ColaboradoresPage />} />
        <Route path="/competencias" element={<CompetenciasPage />} />
        <Route path="/pdi" element={<PdiPage />} />
        <Route path="/ficha/:colaboradorId" element={<FichaPage />} />
        <Route path="/configuracoes" element={<ConfiguracoesPage />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
