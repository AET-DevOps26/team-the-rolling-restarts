import { NextResponse, type NextRequest } from "next/server";

import { AUTH_COOKIE } from "@/lib/auth/constants";

export function middleware(request: NextRequest) {
  if (!request.cookies.get(AUTH_COOKIE)) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/saved/:path*", "/settings/:path*", "/article/:path*"],
};
