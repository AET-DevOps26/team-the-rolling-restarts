"use client";

import { Search } from "lucide-react";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";

import { Input } from "@/components/ui/input";

export function TopbarSearch() {
  const router = useRouter();
  const params = useSearchParams();
  const q = params.get("q") ?? "";
  const [value, setValue] = useState(q);
  const [prevQ, setPrevQ] = useState(q);
  if (q !== prevQ) {
    setPrevQ(q);
    setValue(q);
  }

  function onSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const nextQ = value.trim();
    const next = new URLSearchParams(params.toString());
    if (nextQ) next.set("q", nextQ);
    else next.delete("q");
    next.delete("page");
    const search = next.toString();
    router.push(search ? `/dashboard?${search}` : "/dashboard");
  }

  return (
    <form onSubmit={onSubmit} className="relative ml-auto hidden w-72 md:block">
      <Search
        aria-hidden
        className="pointer-events-none absolute inset-y-0 left-2.5 my-auto size-4 text-muted-foreground"
      />
      <Input
        aria-label="Search articles"
        placeholder="Search articles…"
        className="pl-8"
        value={value}
        onChange={(e) => setValue(e.target.value)}
      />
    </form>
  );
}
