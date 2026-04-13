import { NavLink } from "react-router-dom";

const tabs = [
  { to: "/catalog", label: "상품" },
  { to: "/boxes", label: "외박스" },
  { to: "/cart", label: "장바구니" },
  { to: "/map", label: "도면" },
  { to: "/data", label: "데이터" },
  { to: "/settings", label: "설정" },
  { to: "/print", label: "인쇄" },
];

export default function TopBar() {
  return (
    <header className="topbar">
      <div className="topbar-logo">SCAN Admin</div>
      <nav className="topbar-nav">
        {tabs.map((t) => (
          <NavLink
            key={t.to}
            to={t.to}
            className={({ isActive }) =>
              `topbar-tab${isActive ? " active" : ""}`
            }
          >
            {t.label}
          </NavLink>
        ))}
      </nav>
    </header>
  );
}
