interface FilterSidebarProps {
  categories: string[];
  selected: string;
  onSelect: (category: string) => void;
}

export default function FilterSidebar({
  categories,
  selected,
  onSelect,
}: FilterSidebarProps) {
  return (
    <aside className="filter-sidebar">
      <div className="filter-section">
        <div className="filter-title">카테고리</div>
        <div className="filter-chips">
          <button
            className={`filter-chip${selected === "" ? " active" : ""}`}
            onClick={() => onSelect("")}
          >
            전체
          </button>
          {categories.map((cat) => (
            <button
              key={cat}
              className={`filter-chip${selected === cat ? " active" : ""}`}
              onClick={() => onSelect(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>
    </aside>
  );
}
