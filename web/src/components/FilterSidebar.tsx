interface FilterSidebarProps {
  categories: string[];
  selectedCategory: string;
  onSelectCategory: (category: string) => void;
  imageFilter: string;
  onImageFilter: (v: string) => void;
  locationFilter: string;
  onLocationFilter: (v: string) => void;
}

export default function FilterSidebar({
  categories,
  selectedCategory,
  onSelectCategory,
  imageFilter,
  onImageFilter,
  locationFilter,
  onLocationFilter,
}: FilterSidebarProps) {
  return (
    <aside className="filter-sidebar">
      <div className="filter-section">
        <div className="filter-title">카테고리</div>
        <div className="filter-chips">
          <button
            className={`filter-chip${selectedCategory === "" ? " active" : ""}`}
            onClick={() => onSelectCategory("")}
          >
            전체
          </button>
          {categories.map((cat) => (
            <button
              key={cat}
              className={`filter-chip${selectedCategory === cat ? " active" : ""}`}
              onClick={() => onSelectCategory(cat)}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>
      <div className="filter-section">
        <div className="filter-title">이미지</div>
        <div className="filter-chips">
          <button
            className={`filter-chip${imageFilter === "all" ? " active" : ""}`}
            onClick={() => onImageFilter("all")}
          >
            전체
          </button>
          <button
            className={`filter-chip${imageFilter === "has" ? " active" : ""}`}
            onClick={() => onImageFilter("has")}
          >
            있음
          </button>
          <button
            className={`filter-chip${imageFilter === "none" ? " active" : ""}`}
            onClick={() => onImageFilter("none")}
          >
            없음
          </button>
        </div>
      </div>
      <div className="filter-section">
        <div className="filter-title">위치</div>
        <div className="filter-chips">
          <button
            className={`filter-chip${locationFilter === "all" ? " active" : ""}`}
            onClick={() => onLocationFilter("all")}
          >
            전체
          </button>
          <button
            className={`filter-chip${locationFilter === "has" ? " active" : ""}`}
            onClick={() => onLocationFilter("has")}
          >
            있음
          </button>
          <button
            className={`filter-chip${locationFilter === "none" ? " active" : ""}`}
            onClick={() => onLocationFilter("none")}
          >
            없음
          </button>
        </div>
      </div>
    </aside>
  );
}
