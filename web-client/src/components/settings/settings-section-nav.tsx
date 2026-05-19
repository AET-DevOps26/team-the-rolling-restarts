const SECTIONS = [
  { id: "profile", label: "Profile" },
  { id: "topics", label: "Topics" },
  { id: "sources", label: "Sources" },
  { id: "feed-preferences", label: "Feed preferences" },
  { id: "notifications", label: "Notifications" },
  { id: "appearance", label: "Appearance" },
];

export function SettingsSectionNav() {
  return (
    <nav
      aria-label="Settings sections"
      className="sticky top-20 hidden lg:block"
    >
      <ul className="flex flex-col gap-1 text-sm">
        {SECTIONS.map((section) => (
          <li key={section.id}>
            <a
              href={`#${section.id}`}
              className="block rounded-md px-3 py-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
            >
              {section.label}
            </a>
          </li>
        ))}
      </ul>
    </nav>
  );
}
