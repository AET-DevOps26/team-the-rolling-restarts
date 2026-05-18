import Link from "next/link";

const COLUMNS: { heading: string; links: { label: string; href: string }[] }[] = [
  {
    heading: "Product",
    links: [
      { label: "Features", href: "#" },
      { label: "Sources", href: "#" },
      { label: "Pricing", href: "#" },
    ],
  },
  {
    heading: "Company",
    links: [
      { label: "About", href: "#" },
      { label: "Careers", href: "#" },
      { label: "Press", href: "#" },
    ],
  },
  {
    heading: "Resources",
    links: [
      { label: "Help centre", href: "#" },
      { label: "Status", href: "#" },
      { label: "Contact", href: "#" },
    ],
  },
  {
    heading: "Legal",
    links: [
      { label: "Privacy", href: "#" },
      { label: "Terms", href: "#" },
      { label: "Cookies", href: "#" },
    ],
  },
];

export function MarketingFooter() {
  return (
    <footer className="border-t border-border bg-muted/30">
      <div className="mx-auto grid w-full max-w-6xl gap-10 px-4 py-12 sm:grid-cols-2 md:grid-cols-4">
        {COLUMNS.map((column) => (
          <div key={column.heading} className="flex flex-col gap-3">
            <h4 className="text-sm font-semibold">{column.heading}</h4>
            <ul className="flex flex-col gap-2 text-sm text-muted-foreground">
              {column.links.map((link) => (
                <li key={link.label}>
                  <Link href={link.href} className="hover:text-foreground">
                    {link.label}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
      <div className="border-t border-border px-4 py-4 text-center text-xs text-muted-foreground">
        © {new Date(2026, 4, 14).getFullYear()} NewsLens. All rights reserved.
      </div>
    </footer>
  );
}
