import { lazy, useEffect } from 'react'
import type { ComponentType } from 'react'
import { Route, Routes } from 'react-router-dom'
import Layout, { RequireAuth } from './components/Layout'

/**
 * Every screen is its own chunk, loaded the first time it is visited, so the first paint downloads only the shell.
 * The commonly visited ones are fetched quietly in the background once the browser is idle (see below),
 * so clicking around feels instant.
 */
function lazyNamed<M extends Record<string, unknown>>(load: () => Promise<M>, key: keyof M) {
  return lazy(() => load().then((m) => ({ default: m[key] as ComponentType })))
}

const Home = lazy(() => import('./pages/Home'))
const NotFound = lazy(() => import('./pages/NotFound'))
const Admin = lazy(() => import('./pages/Admin'))
const Profile = lazy(() => import('./pages/Profile'))
const Receive = lazy(() => import('./pages/Receive'))
const VerifyEmail = lazy(() => import('./pages/VerifyEmail'))
const Login = lazyNamed(() => import('./pages/Auth'), 'Login')
const Register = lazyNamed(() => import('./pages/Auth'), 'Register')
const ForgotPassword = lazyNamed(() => import('./pages/Auth'), 'ForgotPassword')
const ResetPassword = lazyNamed(() => import('./pages/Auth'), 'ResetPassword')
const BookingList = lazyNamed(() => import('./pages/Bookings'), 'BookingList')
const BookingDetail = lazyNamed(() => import('./pages/Bookings'), 'BookingDetail')
const MyParcels = lazyNamed(() => import('./pages/Parcels'), 'MyParcels')
const NewParcel = lazyNamed(() => import('./pages/Parcels'), 'NewParcel')
const ParcelDetail = lazyNamed(() => import('./pages/Parcels'), 'ParcelDetail')
const BrowseTrips = lazyNamed(() => import('./pages/Trips'), 'BrowseTrips')
const MyTrips = lazyNamed(() => import('./pages/Trips'), 'MyTrips')
const NewTrip = lazyNamed(() => import('./pages/Trips'), 'NewTrip')
const TripDetail = lazyNamed(() => import('./pages/Trips'), 'TripDetail')
const Terms = lazyNamed(() => import('./pages/Legal'), 'Terms')
const ProhibitedItems = lazyNamed(() => import('./pages/Legal'), 'ProhibitedItems')
const IdentityCallback = lazyNamed(() => import('./pages/Identity'), 'IdentityCallback')
const DigiLockerDemo = lazyNamed(() => import('./pages/Identity'), 'DigiLockerDemo')

export default function App() {
  useEffect(() => {
    // warm the screens people are most likely to open next, without competing with the page that is loading now
    const idle: (cb: () => void) => void = 'requestIdleCallback' in window ? (cb) => window.requestIdleCallback(cb) : (cb) => setTimeout(cb, 2000)
    idle(() => {
      void import('./pages/Trips')
      void import('./pages/Bookings')
      void import('./pages/Parcels')
      void import('./pages/Profile')
    })
  }, [])

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/forgot" element={<ForgotPassword />} />
        <Route path="/reset" element={<ResetPassword />} />
        <Route path="/receive/:token" element={<Receive />} />
        <Route path="/verify" element={<VerifyEmail />} />
        <Route path="/terms" element={<Terms />} />
        <Route path="/prohibited" element={<ProhibitedItems />} />

        <Route element={<RequireAuth />}>
          <Route path="/trips" element={<BrowseTrips />} />
          <Route path="/trips/new" element={<NewTrip />} />
          <Route path="/trips/:id" element={<TripDetail />} />
          <Route path="/my-trips" element={<MyTrips />} />
          <Route path="/parcels" element={<MyParcels />} />
          <Route path="/parcels/new" element={<NewParcel />} />
          <Route path="/parcels/:id" element={<ParcelDetail />} />
          <Route path="/bookings" element={<BookingList />} />
          <Route path="/bookings/:id" element={<BookingDetail />} />
          <Route path="/profile" element={<Profile />} />
          <Route path="/identity/callback" element={<IdentityCallback />} />
          <Route path="/digilocker-demo" element={<DigiLockerDemo />} />
        </Route>

        <Route element={<RequireAuth admin />}>
          <Route path="/admin" element={<Admin />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
