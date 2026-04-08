import type { Metadata, Viewport } from "next";
import "./globals.css";
import { LayoutSwitcher } from "@/components/layout/layout-switcher";
import { Providers } from "./providers";

export const metadata: Metadata = {
  title: "Vajra",
  description: "Vajra Dashboard",
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: "#F8F8F6",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen antialiased" suppressHydrationWarning>
        <Providers>
          <LayoutSwitcher>{children}</LayoutSwitcher>
        </Providers>
      </body>
    </html>
  );
}
