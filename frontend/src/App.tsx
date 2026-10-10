import { Route, Routes } from 'react-router-dom'
import Layout, { RequireAuth } from './components/Layout'
import Admin from './pages/Admin'
import { ForgotPassword, Login, Register, ResetPassword } from './pages/Auth'
import { BookingDetail, BookingList } from './pages/Bookings'
import Home from './pages/Home'
import NotFound from './pages/NotFound'
import { MyParcels, NewParcel, ParcelDetail } from './pages/Parcels'
import { ProhibitedItems, Terms } from './pages/Legal'
import Profile from './pages/Profile'
import Receive from './pages/Receive'
import { BrowseTrips, MyTrips, NewTrip, TripDetail } from './pages/Trips'
import VerifyEmail from './pages/VerifyEmail'

export default function App() {
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
        </Route>

        <Route element={<RequireAuth admin />}>
          <Route path="/admin" element={<Admin />} />
        </Route>

        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
