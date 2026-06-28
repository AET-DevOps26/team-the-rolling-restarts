import { NextResponse, type NextRequest } from "next/server";

const PROTECTED = ["/dashboard", "/saved", "/settings", "/article"];

export function middleware(request: NextRequest) {
  const { pathname } = request.nextUrl;
  const needsAuth = PROTECTED.some(
    (p) => pathname === p || pathname.startsWith(`${p}/`)
  );
  if (needsAuth && !request.cookies.get("auth_token")) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/dashboard/:path*", "/saved/:path*", "/settings/:path*", "/article/:path*"],
};
